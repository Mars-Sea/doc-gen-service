# doc-gen-service 代码评审报告

> 评审日期：2026-09-02 ｜ 评审范围：全部主源码（16 个 Java 文件）、Go SDK、构建与部署配置、测试代码
> 严重程度定义：**严重** = 安全漏洞/数据错误/构建失效，须尽快修复；**一般** = 明确缺陷或风险，应排期修复；**建议** = 可读性、一致性、可维护性优化

---

## 一、总体评价

| 维度 | 评价 |
|------|------|
| 架构分层 | ★★★★☆ controller → service → util/config 分层清晰，职责单一，无循环依赖 |
| 业务正确性 | ★★★★☆ 核心渲染逻辑正确，边界处理（空数据、占位符空格、循环行误删）考虑周到 |
| 安全性 | ★★☆☆☆ 无认证授权、存在 SSRF 面、错误信息泄露服务器路径 |
| 性能 | ★★★☆☆ 单文档场景良好；批量 Word 全内存合并存在 OOM 风险 |
| 可测试性 | ★★★★☆ 7 个测试类 2699 行，覆盖服务/控制器/工具，含边界用例 |
| 可维护性 | ★★★★☆ Javadoc 详尽、注释解释了历史 bug 根因；存在少量重复代码与文档漂移 |

**统计：严重 3 项，一般 9 项，建议 11 项。**

---

## 二、按模块评审结论

### 2.1 安全与访问控制（跨模块）

- **【严重】全接口无认证/授权。** `pom.xml` 无 spring-boot-starter-security，代码中无 API Key / 过滤器。`POST /api/v1/template/upload`、`DELETE /api/v1/template/{name}` 等写操作可被任意客户端调用：上传接口会静默覆盖同名模板（`TemplateService.uploadTemplate` 用 `REPLACE_EXISTING`），攻击者可替换模板实施内容注入；`/word/batch` 可被滥用为免费算力。`docker-compose.yml` 直接映射 `8081:8081`，一旦宿主机端口暴露即为高危。建议：至少加 API Token（Filter/Interceptor），生产部署置于内网 + 网络隔离。
- **【严重】SSRF（服务端请求伪造）。** `ImagePayloadConverter.downloadImage()` 接受请求方提供的任意 http/https URL 并由服务端发起 GET，且 `setInstanceFollowRedirects(true)` 可经 302 跳转到内网地址（含云厂商元数据 169.254.169.254、内网 actuator 等）。仅有协议白名单与 10MB/超时限制，无私网 IP/解析后地址黑名单。建议：解析域名后校验 IP 是否为私网/回环/链路本地，重定向后重新校验，或改用固定代理出口。
- **【一般】错误信息泄露服务器路径。** `TemplateNotFoundException` 消息为 `"Template not found at: " + templatePath`（绝对路径），`GlobalExceptionHandler` 将 `IOException`/兜底异常的 `e.getMessage()` 原样放进响应体。建议：对外返回通用文案，路径细节仅写日志。
- **【一般】日志注入。** `fileName`、`templateName` 等用户输入未做换行/控制字符过滤即进入 `log.info`，可伪造日志行。建议规范化后再记录。

### 2.2 构建与依赖

- **【严重】`com/deepoove/poi/xwpf/` 补丁类未参与构建，属"幽灵代码"。** 该目录（`AbstractXWPFDocumentMerge.java` 52 行、`XmlXWPFDocumentMerge.java` 447 行）被 git 跟踪，但：① pom 未将其加入编译源；② `target/classes` 与打包 jar 中均无对应 class；③ 根目录残留手工 javac 的 `javac.20260902_163830.args`（输出到 `C:/tmp/build/classes`）。即运行时实际执行的是 poi-tl 官方 jar 中的同名类。若该补丁是为修复批量合并缺陷（README v0.0.4 所述），则修复从未在构建产物中生效；若是实验遗留，应立即删除并在 AGENTS.md 澄清。二选一：要么用 maven-build-helper/独立模块正式纳入构建并写明覆盖 poi-tl 类的原因，要么移出仓库。
- **【一般】依赖版本治理缺失。** Spring Boot 3.2.1（2023-12）已落后多个补丁版本（3.2.x 线后续含 Tomcat/Spring 修复）；CI（`.github/workflows/ci.yml`）仅 `spotless:check` + `mvn test`，无依赖漏洞扫描（Dependabot / OWASP dependency-check），也无 Docker 构建验证。建议升级 Boot 至 3.3+，并启用 Dependabot。
- **【建议】Spotless 未绑定构建生命周期**，本地 `mvn package` 不强制格式，仅 CI 拦截；可绑定到 `validate`/`check` 阶段。
- **【建议】工作区与遗留文件：** 根目录存在 `compile.log`、`javac.*.args`；git 工作区有大量未提交的删除（`.agents/`、`.claude/`、`.codex/`、`.pi/` 等 Trellis 遗留），应尽快提交清理，避免 CI 与本地状态不一致。

### 2.3 `config/` 包

- **【建议】`GlobalExceptionHandler`：`@ExceptionHandler(Exception.class)` 会接管 Spring MVC 内建异常**——JSON 解析失败（HttpMessageNotReadableException）、请求方法不支持（405）、multipart 超限（MaxUploadSizeExceededException，上传 >50MB 模板时）都会落入兜底处理器返回 500 + `INTERNAL_ERROR`，与文档承诺的 400/413 语义不符。建议补充针对性 handler（至少 HttpMessageNotReadableException → 400、MaxUploadSizeExceededException/MultipartException → 413）。
- **【建议】`MethodArgumentNotValidException` 只返回第一个字段错误**，调用方（Go 服务）排障需多次往返；建议返回全部字段错误列表。
- **【建议】`TemplateInitializer` 仅 `@Profile("dev")` 生效**，README/AGENTS 均未说明；其 `createLoopTable` 方法体内混用全限定名（`org.apache.poi.xwpf.usermodel.XWPFTable`）与 import，风格不一致。另：`application.yml` 的 `show-details: when_authorized` 在未引入 Spring Security 时等效于 `never`，配置意图未达成（无害）。
- `DocGenProperties`、`OpenApiConfig`：**【建议】OpenAPI 版本号写死 `v1.0.0`，与 pom `0.0.6` 漂移**，建议从 pom/MANIFEST 读取。

### 2.4 `controller/` 包

- `DocController`（349 行）：**【建议】四个端点中"文件名清洗 + 默认名 + URLEncoder + Content-Disposition 双写 + MediaType + 空文件校验"完全重复 4 次**（约 30 行 × 4）。建议抽取 `buildAttachment(byte[] bytes, String fileName, String ext, String docxMediaType)` 辅助方法，可减少约 100 行并消除四处漂移风险。
- `TemplateController.downloadTemplate`：**【建议】Content-Disposition 直接拼接 `templateName`**，未像 `DocController` 那样做非法字符清洗与 UTF-8 编码（Tomcat 会拦截 CR/LF 故无头注入实害，但含空格/引号的中文模板名会导致下载文件名异常）。建议与 DocController 统一。
- 控制器整体：参数校验（`@Valid` + `@NotBlank/@NotEmpty`）、Swagger 注解、日志齐全，质量良好。

### 2.5 `service/` 包

#### WordService（551 行，核心复杂度所在）

- **【一般】XWPFTemplate 资源泄漏边界。** `generateWord`/`renderTemplateInstance` 中 `try (XWPFTemplate template = XWPFTemplate.compile(templateFile, config).render(processedData); ...)`——若 `render()` 抛异常，赋值未完成，已编译并持有 XWPFDocument 的 template 不会被关闭，高频失败场景（坏模板、SpEL 异常）会累积泄漏。建议：先 `compile` 赋值进入 try-with-resources，再在体内 `render`。
- **【一般】`/word/batch` 无数据量上限。** `WordBatchRequest.dataList` 仅 `@NotEmpty`，逐条渲染 + `merge()` 全程内存操作，合并过程对 body 做 XML 字符串化 + 正则替换（`XmlXWPFDocumentMerge`），内存近似线性放大；50MB 请求体可换出远超其体积的堆占用 → OOM 风险。且每条数据的图片载荷会触发顺序外网下载（每个最大 10MB），放大单请求时延。建议：限制 dataList 条数（如 ≤200）并在 DTO 校验中体现。
- **【建议】单文档生成时模板被解析两次**：`detectLoopTableFields` 打开一次（含全部 XML 解析），`XWPFTemplate.compile` 再一次。批量场景已复用 `loopFields`，单文档场景可合并为一次编译前检测，或按 (path, mtime) 缓存 loopFields。
- **【建议】`detectLoopTableFields` 解析失败静默降级为空集合**：循环表格将不被识别，`[field]` 占位行会原样输出到成品文档（数据缺失但请求"成功"）。降级策略已在注释中说明，属于有意权衡，但建议至少在响应不可见处（文档 debug 头或日志 metric）暴露降级事件，便于排障。
- 亮点：`LoopRowTableCleanupPolicy` 对 poi-tl 触发行残留的处理正确（先渲染后重定位 `indexOf` 再删除）；`LOOP_ROW_TAG`/`TEMPLATE_TAG` 正则对旧版"方括号文本误判为循环行"的修复（注释记录了根因）严谨；SpEL 非严格模式 + `el.trim()` 处理了空格占位符与 null 级联；`generateBatch` 的 `catch (IOException | RuntimeException e) { throw e; }` 保住了 4xx 语义不被 500 吞掉，finally 关闭文档防泄漏，是正确且少见的细致处理。

#### ExcelService（244 行）

- **【一般】`listData` 中的图片载荷不转换。** `preprocessImagePayloads` 仅处理顶层 `data`；Word 端 `convertImagePayload` 是递归的，Excel 端不是——若用户在 `{.field}` 循环行内放 `{logo}` 图片变量，将得到 `Map.toString()` 文本或空单元格。两端行为不一致，建议递归转换 listData 元素或明确文档化不支持。
- **【建议】`fillTemplate` 仅作用于第一个 sheet**（`EasyExcel.writerSheet()` 无参默认），多 sheet 模板不生效，README 未标注；`FillConfig.forceNewRow(TRUE)` 在大数据量时整表进内存（EasyExcel 已知权衡），可文档化。
- **【建议】异常包装丢失语义。** `generateExcel`/`fillTemplate` 将底层异常统一包成 `RuntimeException`（message 含 `e.getMessage()`），落入兜底 500 并向客户端透传内部细节；建议引入业务异常（如 `DocGenerationException`）+ 错误码，与第 2.1 节信息泄露问题一并处理。
- 亮点：先填列表后填单值的顺序正确（列表改变行数）；图片走魔术字节校验后的 `ImageData` 组装与官方示例一致。

#### TemplateService（206 行）

- **【一般】上传非原子。** `Files.copy(REPLACE_EXISTING)` 直接覆盖目标文件，并发"渲染读取 + 上传覆盖"可读到半写文件导致渲染失败。建议写同目录临时文件后 `Files.move(ATOMIC_MOVE)`。
- **【建议】校验逻辑与 `TemplateValidationUtil` 重复且不一致**：本类自行实现 `contains("..")/"/" "\\"` 检查，未复用工具类；上传只允许 `.docx/.xlsx`，而 `validateExcelTemplateExtension` 允许 `.xls`——上传入口与使用入口规则不一致，应统一（若支持 .xls 则上传放行，否则生成端拒绝）。
- 亮点：删除/下载均校验 `isRegularFile`，防目录删除与目录遍历；目录不存在时优雅返回空列表。

### 2.6 `util/` 包

#### ImagePayloadConverter（424 行）

- 亮点（值得肯定的安全设计）：下载内容以**魔术字节**判定真实格式（防 HTML 错误页被嵌入 docx）；10MB 上限 + 连接/读取超时；空内容拒绝；URL 后缀 `.php/.do` 不预判格式避免误杀。
- SSRF 缺口见 2.1 节（**严重**）。
- **【建议】** `validateUrl` 用 `URI.create(url)` 后，`downloadImage` 又 `new URL(url)` 二次解析，两处解析结果可能不一致（URI 对某些字符更宽松）；建议统一一次解析。`new URL()` 在 Java 20+ 已弃用，升级 JDK 时需改为 `URI.toURL()`。
- **【建议】** `resolveFormat` 中 `inferred` 变量命名与其语义（"声明/推断格式"）不符，建议改名 `declaredOrInferred`。

#### TemplateValidationUtil（80 行）

- **【一般】未拦截 Windows 盘符冒号**：`validateTemplateName` 检查 `..`、`/`、`\` 及 `getNameCount()!=1`，但不含 `:`。Windows 下 `C:evil.docx` 这类 drive-relative 路径可能通过校验（当前 Linux/Docker 部署无实害，跨平台复用该工具类时有风险）。建议补 `:` 检查，并限制文件名长度与控制字符。

### 2.7 `model/` 与 `exception/` 包

- DTO 使用 `@NotBlank/@NotEmpty` + Swagger 注解 + 详尽 Javadoc，质量好。**【建议】** `WordBatchRequest` 缺少数量上限校验（见 2.5）；`ExcelGenRequest` 可加 `@Size` 限制行数。
- 自定义异常携带上下文字段（`templateName`/`fieldName`）设计良好；`TemplateNotFoundException` 的 message 含绝对路径问题见 2.1。

### 2.8 Go SDK（`sdk/go/`，4 文件）

- 亮点：`ErrorResponse` 实现 `error` 接口；300s 默认超时的注释解释了"服务端仍处理中客户端提前断开 → Broken pipe"的真实案例；`url.PathEscape` 处理中文模板名。
- **【建议】** `client.go` 的 `createFile` 全局变量 + `file.go init()` 覆盖的"兼容性封装"过度设计（且默认实现会返回 error），直接 `os.Create` 即可；`SaveTemplate` 用 `os.WriteFile` 而未走 `writeFile`，两套写文件路径并存。
- **【建议】** `template.go` 中 `UploadTemplate`/`UploadTemplateFromBytes`、`ListTemplates`/`ListTemplatesWithDetails` 存在大段复制粘贴（错误处理逻辑重复 6 次），建议抽取 `doJSON(req) ([]byte, error)` 通用方法；SDK 无任何单元测试。
- **【建议】** `client.go` 中 `Client.BaseURL` 末尾若带 `/` 会拼出双斜杠（`fmt.Sprintf("%s%s")`），建议 `strings.TrimSuffix(baseURL, "/")`。

### 2.9 测试代码（7 类 / 2699 行）

- 优点：覆盖模板缺失、路径遍历、非法扩展名、占位符空格、空 data、循环表格、多表格、图片载荷等真实边界；`@TempDir` 隔离文件系统。
- **【建议】** 无 `GlobalExceptionHandler` 对 HttpMessageNotReadableException/MaxUploadSizeExceededException 的行为断言（正是第 2.3 节缺陷未被测试暴露的原因）；`ImagePayloadConverterTest` 依赖真实外网下载的话会不稳定（建议用本地 HttpServer stub）；Go SDK 与 `LoopRowTableCleanupPolicy` 的触发行删除路径无直接测试。

### 2.10 部署（Dockerfile / docker-compose / CI）

- 亮点：healthcheck、多架构构建、模板目录外置挂载、actuator 仅暴露 health/info。
- **【建议】** Dockerfile 未声明 `USER`，容器以 root 运行，建议加非 root 用户；`COPY target/*.jar` 依赖"先在宿主机 mvn package"的隐含前置（AGENTS.md 已说明，可加注释）；CI 缺 `docker build` 冒烟与漏洞扫描（见 2.2）。

---

## 三、修复优先级建议

1. **立即**：确认并处置 `com/deepoove` 补丁（纳入构建或删除）；为全部接口加最简 API Token；SSRF 私网地址校验。
2. **本迭代**：补 Spring MVC 内建异常 handler（400/405/413）；错误响应去路径化；批量 Word 条数上限；XWPFTemplate 关闭边界修正；上传原子写。
3. **后续**：DocController 重复代码抽取；Excel listData 图片一致性；依赖升级 + Dependabot；Dockerfile 非 root；Go SDK 去重 + 测试；文档版本号对齐。

## 四、亮点小结

- 错误语义设计完整：422（模板缺失）/400（校验）/422→500 分层，`catch(IOException|RuntimeException) throw e` 保语义不被包装。
- 图片链路的魔术字节校验、大小/超时限制是同类项目少见的安全意识。
- 注释记录历史 bug 根因（如循环行误删、SpEL 非严格模式选择），对后来者价值很高。
- 测试贴近真实故障场景（占位符空格、多表格、批次合并）。
